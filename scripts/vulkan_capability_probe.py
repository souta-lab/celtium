#!/usr/bin/env python3
"""Standalone Vulkan capability probe - the out-of-game bootstrap report.

Answers, per Vulkan physical device, without Minecraft, without a JVM and without creating a
VkDevice (so it is safe to run while a game instance is live):

  * identity: name, vendor / device ids, device type, API and driver versions;
  * the limits celtium's device selection and its advertised capability answers are checked
    against - maxPushConstantsSize (with the renderer's >= 256 gate verdict from
    VulkanContext), maxColorAttachments, the per-stage descriptor ceilings and
    maxImageDimension2D;
  * the storage-image features the image-unit advertising reads at selection time
    (shaderStorageImageReadWithoutFormat / WriteWithoutFormat / ExtendedFormats);
  * device extensions: the total count, and presence of the ones celtium's paths ask for
    (swapchain, dynamic rendering, host image copy, external memory / semaphore fd);
  * queue families with their flag sets;
  * present support, when a Wayland display is reachable (WAYLAND_DISPLAY set and
    libwayland-client loadable): a real wl_surface is created and
    vkGetPhysicalDeviceSurfaceSupportKHR is asked per queue family. Without a display the
    report says present support was not checked - it never guesses.

This is the instrument for isolating a platform whose stack dies inside the game: run it on
the bare machine first. The sibling vulkan_depth_format_probe.py covers the depth-format
contract specifically.

Run it once per driver with VK_ICD_FILENAMES set, e.g.

  VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/radeon_icd.json scripts/vulkan_capability_probe.py
  VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json  scripts/vulkan_capability_probe.py

On a Wayland session, present support is checked when the session environment is present:

  XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-1 scripts/vulkan_capability_probe.py
"""
import ctypes
import os
import sys

VK_SUCCESS = 0

VK_STRUCTURE_TYPE_APPLICATION_INFO = 0
VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1
VK_STRUCTURE_TYPE_WAYLAND_SURFACE_CREATE_INFO_KHR = 1000006000

VK_QUEUE_GRAPHICS_BIT = 0x00000001
VK_QUEUE_COMPUTE_BIT = 0x00000002
VK_QUEUE_TRANSFER_BIT = 0x00000004
VK_QUEUE_SPARSE_BINDING_BIT = 0x00000008

DEVICE_TYPES = {0: "OTHER", 1: "INTEGRATED_GPU", 2: "DISCRETE_GPU", 3: "VIRTUAL_GPU", 4: "CPU"}

# The hard device-selection gate in VulkanContext (PUSH_CONSTANT_BYTES_USED): a device below
# this is refused in game, so the probe prints the same verdict out of game.
PUSH_CONSTANT_BYTES_USED = 256

WANTED_EXTENSIONS = [
    "VK_KHR_swapchain",
    "VK_KHR_dynamic_rendering",
    "VK_EXT_host_image_copy",
    "VK_KHR_external_memory_fd",
    "VK_KHR_external_semaphore_fd",
]

# Byte offsets into a raw VkPhysicalDeviceProperties buffer (vulkan_core.h layout):
# 5 x u32 header, deviceName[256] at 20, pipelineCacheUUID[16] at 276 - and then 4 bytes of
# padding, because the struct's alignment is 8 (VkPhysicalDeviceLimits contains VkDeviceSize
# members), so limits starts at 296, not 292. The field offsets inside limits are the
# header's own (maxColorAttachments is at +392 in the current header, not the +312 of the
# abbreviated layout one might reconstruct from memory - the viewport/alignment block sits
# between the fragment and compute sections).
OFF_API_VERSION = 0
OFF_DRIVER_VERSION = 4
OFF_VENDOR_ID = 8
OFF_DEVICE_ID = 12
OFF_DEVICE_TYPE = 16
OFF_DEVICE_NAME = 20
LIMITS = 296
OFF_MAX_IMAGE_DIMENSION_2D = LIMITS + 4
OFF_MAX_PUSH_CONSTANTS_SIZE = LIMITS + 32
OFF_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS = LIMITS + 68
OFF_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES = LIMITS + 80
OFF_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES = LIMITS + 84
OFF_MAX_DESCRIPTOR_SET_STORAGE_IMAGES = LIMITS + 120
OFF_MAX_COLOR_ATTACHMENTS = LIMITS + 392

# VkPhysicalDeviceFeatures is 55 VkBool32 in core order; only these indices are read.
FEAT_STORAGE_IMAGE_EXTENDED_FORMATS = 29
FEAT_STORAGE_IMAGE_READ_WITHOUT_FORMAT = 31
FEAT_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT = 32


class VkApplicationInfo(ctypes.Structure):
    _fields_ = [
        ("sType", ctypes.c_uint32),
        ("pNext", ctypes.c_void_p),
        ("pApplicationName", ctypes.c_char_p),
        ("applicationVersion", ctypes.c_uint32),
        ("pEngineName", ctypes.c_char_p),
        ("engineVersion", ctypes.c_uint32),
        ("apiVersion", ctypes.c_uint32),
    ]


class VkInstanceCreateInfo(ctypes.Structure):
    _fields_ = [
        ("sType", ctypes.c_uint32),
        ("pNext", ctypes.c_void_p),
        ("flags", ctypes.c_uint32),
        ("pApplicationInfo", ctypes.c_void_p),
        ("enabledLayerCount", ctypes.c_uint32),
        ("ppEnabledLayerNames", ctypes.c_void_p),
        ("enabledExtensionCount", ctypes.c_uint32),
        ("ppEnabledExtensionNames", ctypes.c_void_p),
    ]


class VkWaylandSurfaceCreateInfo(ctypes.Structure):
    _fields_ = [
        ("sType", ctypes.c_uint32),
        ("pNext", ctypes.c_void_p),
        ("flags", ctypes.c_uint32),
        ("display", ctypes.c_void_p),
        ("surface", ctypes.c_void_p),
    ]


def load_loader():
    for name in ("libvulkan.so.1", "libvulkan.so"):
        try:
            return ctypes.CDLL(name)
        except OSError:
            continue
    raise SystemExit("no Vulkan loader found")


def version_text(v):
    return "%d.%d.%d" % ((v >> 22) & 0x3FF, (v >> 12) & 0x3FF, v & 0xFFF)


def u32(buf, off):
    return ctypes.c_uint32.from_buffer(buf, off).value


class Probe:
    def __init__(self):
        self.vk = load_loader()
        vk = self.vk
        vk.vkCreateInstance.restype = ctypes.c_int
        vk.vkCreateInstance.argtypes = [ctypes.c_void_p, ctypes.c_void_p,
                                        ctypes.POINTER(ctypes.c_void_p)]
        vk.vkEnumeratePhysicalDevices.argtypes = [
            ctypes.c_void_p, ctypes.POINTER(ctypes.c_uint32), ctypes.POINTER(ctypes.c_void_p)]
        vk.vkGetInstanceProcAddr.restype = ctypes.c_void_p
        vk.vkGetInstanceProcAddr.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        self.instance = ctypes.c_void_p()
        self.surface = ctypes.c_void_p()
        self.wayland = None
        self.wl_display = None
        self.wl_surface = None

    def proc(self, name):
        addr = self.vk.vkGetInstanceProcAddr(self.instance, name.encode())
        return addr

    def instance_extensions(self):
        vk = self.vk
        vk.vkEnumerateInstanceExtensionProperties.argtypes = [
            ctypes.c_char_p, ctypes.POINTER(ctypes.c_uint32), ctypes.c_void_p]
        count = ctypes.c_uint32(0)
        vk.vkEnumerateInstanceExtensionProperties(None, ctypes.byref(count), None)
        raw = ctypes.create_string_buffer(260 * max(count.value, 1))
        vk.vkEnumerateInstanceExtensionProperties(None, ctypes.byref(count), raw)
        names = []
        for i in range(count.value):
            names.append(raw.raw[i * 260:i * 260 + 256].split(b"\0")[0].decode("utf-8", "replace"))
        return names

    def open_instance(self, enable):
        vk = self.vk
        app = VkApplicationInfo(VK_STRUCTURE_TYPE_APPLICATION_INFO, None,
                                b"celtium-capability-probe", 1, b"celtium-capability-probe", 1,
                                (1 << 22))
        names = [ctypes.c_char_p(n.encode()) for n in enable]
        arr = (ctypes.c_char_p * len(names))(*[n.value for n in names]) if names else None
        create = VkInstanceCreateInfo(
            VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, None, 0,
            ctypes.cast(ctypes.byref(app), ctypes.c_void_p), 0, None,
            len(names), ctypes.cast(arr, ctypes.c_void_p) if arr else None)
        result = vk.vkCreateInstance(ctypes.byref(create), None, ctypes.byref(self.instance))
        if result != VK_SUCCESS:
            raise SystemExit("vkCreateInstance failed: %d" % result)

    def try_wayland_surface(self):
        """Best effort: a real wl_surface on the session compositor, or None with a reason."""
        display_name = os.environ.get("WAYLAND_DISPLAY")
        if not display_name:
            return None, "WAYLAND_DISPLAY not set"
        try:
            wl = ctypes.CDLL("libwayland-client.so.0")
        except OSError:
            return None, "libwayland-client.so.0 not loadable"
        wl.wl_display_connect.restype = ctypes.c_void_p
        wl.wl_display_connect.argtypes = [ctypes.c_char_p]
        display = wl.wl_display_connect(display_name.encode())
        if not display:
            return None, "wl_display_connect(%s) failed" % display_name

        def iface_addr(name):
            # The address of a wl_interface data symbol in the library.
            return ctypes.addressof(ctypes.c_char.in_dll(wl, name))

        # Variadic in C; called without argtypes so ctypes passes each argument as given.
        marshal = wl.wl_proxy_marshal_constructor
        marshal.restype = ctypes.c_void_p

        state = {"compositor": None}
        GLOBAL_CB = ctypes.CFUNCTYPE(
            None, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_char_p,
            ctypes.c_uint32)

        def on_global(data, registry, name, interface, version):
            if interface == b"wl_compositor" and not state["compositor"]:
                # wl_registry.bind (opcode 0): args are (name, interface-name, version);
                # the new_id is created from the interface struct passed here.
                state["compositor"] = marshal(
                    registry, 0, ctypes.c_void_p(iface_addr("wl_compositor_interface")),
                    ctypes.c_uint32(name), ctypes.c_char_p(b"wl_compositor"),
                    ctypes.c_uint32(min(version, 4)))

        class Listener(ctypes.Structure):
            _fields_ = [("global", GLOBAL_CB), ("remove", GLOBAL_CB)]

        cb = GLOBAL_CB(on_global)
        noop = GLOBAL_CB(lambda *a: None)
        listener = Listener(cb, noop)
        self._keepalive = [cb, noop, listener]

        wl.wl_display_roundtrip.restype = ctypes.c_int
        wl.wl_display_roundtrip.argtypes = [ctypes.c_void_p]
        wl.wl_proxy_add_listener.restype = ctypes.c_int
        wl.wl_proxy_add_listener.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]

        # wl_display_get_registry is a header inline over the marshal (wl_display
        # opcode 1), not an exported symbol.
        registry = marshal(display, 1, ctypes.c_void_p(iface_addr("wl_registry_interface")))
        if not registry:
            wl.wl_display_disconnect(display)
            return None, "wl_display_get_registry marshal failed"
        wl.wl_proxy_add_listener(registry, ctypes.byref(listener), None)
        wl.wl_display_roundtrip(display)
        if not state["compositor"]:
            wl.wl_display_disconnect(display)
            return None, "compositor did not advertise wl_compositor"
        # wl_compositor.create_surface (opcode 0): no arguments besides the new_id.
        surface = marshal(state["compositor"], 0,
                          ctypes.c_void_p(iface_addr("wl_surface_interface")))
        if not surface:
            wl.wl_display_disconnect(display)
            return None, "wl_compositor_create_surface failed"
        wl.wl_display_roundtrip(display)
        self.wayland = wl
        self.wl_display = display
        self.wl_surface = surface
        return surface, None

    def open_surface(self):
        try:
            wl_surface, reason = self.try_wayland_surface()
        except Exception as exc:  # a probe reports; it does not crash the report
            return "wayland attempt failed: %r" % (exc,)
        if not wl_surface:
            return reason
        create_addr = self.proc("vkCreateWaylandSurfaceKHR")
        support_addr = self.proc("vkGetPhysicalDeviceSurfaceSupportKHR")
        if not create_addr or not support_addr:
            return "surface entry points missing from the loader"
        create_fn = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p,
                                     ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p))(
            create_addr)
        info = VkWaylandSurfaceCreateInfo(
            VK_STRUCTURE_TYPE_WAYLAND_SURFACE_CREATE_INFO_KHR, None, 0,
            self.wl_display, self.wl_surface)
        result = create_fn(self.instance, ctypes.byref(info), None, ctypes.byref(self.surface))
        if result != VK_SUCCESS:
            return "vkCreateWaylandSurfaceKHR failed: %d" % result
        self.support_fn = ctypes.CFUNCTYPE(
            ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_uint32))(support_addr)
        return None

    def devices(self):
        vk = self.vk
        count = ctypes.c_uint32(0)
        vk.vkEnumeratePhysicalDevices(self.instance, ctypes.byref(count), None)
        devices = (ctypes.c_void_p * max(count.value, 1))()
        vk.vkEnumeratePhysicalDevices(self.instance, ctypes.byref(count), devices)
        return [devices[i] for i in range(count.value)]

    def report_device(self, index, physical):
        vk = self.vk
        vk.vkGetPhysicalDeviceProperties.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        vk.vkGetPhysicalDeviceFeatures.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        vk.vkEnumerateDeviceExtensionProperties.argtypes = [
            ctypes.c_void_p, ctypes.c_char_p, ctypes.POINTER(ctypes.c_uint32), ctypes.c_void_p]
        vk.vkGetPhysicalDeviceQueueFamilyProperties.argtypes = [
            ctypes.c_void_p, ctypes.POINTER(ctypes.c_uint32), ctypes.c_void_p]

        props = ctypes.create_string_buffer(2048)
        vk.vkGetPhysicalDeviceProperties(physical, props)
        name = props.raw[OFF_DEVICE_NAME:OFF_DEVICE_NAME + 256].split(b"\0")[0].decode(
            "utf-8", "replace")
        print("device %d: %s" % (index, name))
        print("  vendor=%04x device=%04x type=%s api=%s driver=0x%08x" % (
            u32(props, OFF_VENDOR_ID), u32(props, OFF_DEVICE_ID),
            DEVICE_TYPES.get(u32(props, OFF_DEVICE_TYPE), "?"),
            version_text(u32(props, OFF_API_VERSION)), u32(props, OFF_DRIVER_VERSION)))
        push = u32(props, OFF_MAX_PUSH_CONSTANTS_SIZE)
        print("  maxPushConstantsSize=%d -> celtium gate (>= %d): %s" % (
            push, PUSH_CONSTANT_BYTES_USED,
            "PASS" if push >= PUSH_CONSTANT_BYTES_USED else "REFUSED"))
        print("  maxColorAttachments=%d maxImageDimension2D=%d" % (
            u32(props, OFF_MAX_COLOR_ATTACHMENTS), u32(props, OFF_MAX_IMAGE_DIMENSION_2D)))
        print("  perStage: samplers=%d sampledImages=%d storageImages=%d; descriptorSet "
              "storageImages=%d" % (
                  u32(props, OFF_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS),
                  u32(props, OFF_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES),
                  u32(props, OFF_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES),
                  u32(props, OFF_MAX_DESCRIPTOR_SET_STORAGE_IMAGES)))

        feats = (ctypes.c_uint32 * 55)()
        vk.vkGetPhysicalDeviceFeatures(physical, feats)
        print("  storageImage: extendedFormats=%s readWithoutFormat=%s "
              "writeWithoutFormat=%s" % (
                  bool(feats[FEAT_STORAGE_IMAGE_EXTENDED_FORMATS]),
                  bool(feats[FEAT_STORAGE_IMAGE_READ_WITHOUT_FORMAT]),
                  bool(feats[FEAT_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT])))

        count = ctypes.c_uint32(0)
        vk.vkEnumerateDeviceExtensionProperties(physical, None, ctypes.byref(count), None)
        raw = ctypes.create_string_buffer(260 * max(count.value, 1))
        vk.vkEnumerateDeviceExtensionProperties(physical, None, ctypes.byref(count), raw)
        have = set()
        for i in range(count.value):
            have.add(raw.raw[i * 260:i * 260 + 256].split(b"\0")[0].decode("utf-8", "replace"))
        print("  extensions=%d; celtium set: %s" % (
            count.value,
            ", ".join("%s=%s" % (e, "yes" if e in have else "MISSING")
                      for e in WANTED_EXTENSIONS)))

        qcount = ctypes.c_uint32(0)
        vk.vkGetPhysicalDeviceQueueFamilyProperties(physical, ctypes.byref(qcount), None)
        qraw = ctypes.create_string_buffer(24 * max(qcount.value, 1))
        vk.vkGetPhysicalDeviceQueueFamilyProperties(physical, ctypes.byref(qcount), qraw)
        for qi in range(qcount.value):
            base = qi * 24
            flags = u32(qraw, base)
            n = u32(qraw, base + 4)
            bits = []
            if flags & VK_QUEUE_GRAPHICS_BIT:
                bits.append("GRAPHICS")
            if flags & VK_QUEUE_COMPUTE_BIT:
                bits.append("COMPUTE")
            if flags & VK_QUEUE_TRANSFER_BIT:
                bits.append("TRANSFER")
            if flags & VK_QUEUE_SPARSE_BINDING_BIT:
                bits.append("SPARSE")
            line = "  queueFamily %d: count=%d flags=%s" % (qi, n, "|".join(bits) or "none")
            if self.surface:
                supported = ctypes.c_uint32(0)
                result = self.support_fn(physical, qi, self.surface,
                                         ctypes.byref(supported))
                line += " present=%s" % (
                    ("yes" if supported.value else "no") if result == VK_SUCCESS
                    else "error(%d)" % result)
            print(line)


def main():
    probe = Probe()
    available = probe.instance_extensions()
    enable = [e for e in ("VK_KHR_surface", "VK_KHR_wayland_surface") if e in available]
    probe.open_instance(enable)
    surface_note = None
    if "VK_KHR_wayland_surface" in enable:
        surface_note = probe.open_surface()
    else:
        surface_note = "instance lacks VK_KHR_wayland_surface"
    icds = os.environ.get("VK_ICD_FILENAMES") or os.environ.get("VK_DRIVER_FILES") \
        or "(loader default)"
    print("ICD: %s" % icds)
    print("instance extensions enabled: %s" % (", ".join(enable) or "none"))
    if probe.surface:
        print("present support: checked against a live Wayland surface")
    else:
        print("present support: NOT CHECKED (%s)" % surface_note)
    devices = probe.devices()
    if not devices:
        print("no physical devices")
    for index, physical in enumerate(devices):
        probe.report_device(index, physical)
    if probe.surface:
        destroy = probe.proc("vkDestroySurfaceKHR")
        if destroy:
            ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p)(
                destroy)(probe.instance, probe.surface, None)
    probe.vk.vkDestroyInstance(probe.instance, None)
    print("")


if __name__ == "__main__":
    main()
