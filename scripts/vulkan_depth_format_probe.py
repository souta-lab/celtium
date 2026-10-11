#!/usr/bin/env python3
"""Standalone depth-format probe for the GL-less owned frame target contract.

Answers, per Vulkan physical device, without creating a VkDevice (so it is safe to run while a
game instance is live):

  * vkGetPhysicalDeviceFormatProperties for every depth / depth-stencil candidate, and whether
    optimalTilingFeatures carries DEPTH_STENCIL_ATTACHMENT_BIT and SAMPLED_IMAGE_BIT - the two
    bits the owned target needs for a depth attachment that can also be sampled;
  * vkGetPhysicalDeviceImageFormatProperties for 2D + OPTIMAL + DEPTH_STENCIL_ATTACHMENT|SAMPLED,
    which is the creatable-image question rather than the feature question.

Run it once per driver with VK_ICD_FILENAMES set, e.g.

  VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/radeon_icd.json scripts/vulkan_depth_format_probe.py
  VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json  scripts/vulkan_depth_format_probe.py
"""
import ctypes
import os
import sys

VK_SUCCESS = 0
VK_ERROR_FORMAT_NOT_SUPPORTED = -11

VK_IMAGE_TYPE_2D = 1
VK_IMAGE_TILING_OPTIMAL = 0
VK_IMAGE_TILING_LINEAR = 1
VK_IMAGE_USAGE_SAMPLED_BIT = 0x00000004
VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000020

VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT = 0x00000001
VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000200

VK_STRUCTURE_TYPE_APPLICATION_INFO = 0
VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1

# From /usr/include/vulkan/vulkan_core.h on this machine (matches the LWJGL 3.4.1 constants the
# renderer uses). Note that these are the current core numbering, not the 1.0 header numbering.
CANDIDATES = [
    ("D16_UNORM", 124, False),
    ("X8_D24_UNORM_PACK32", 125, False),
    ("D32_SFLOAT", 126, False),
    ("S8_UINT", 127, True),
    ("D16_UNORM_S8_UINT", 128, True),
    ("D24_UNORM_S8_UINT", 129, True),
    ("D32_SFLOAT_S8_UINT", 130, True),
]


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


class VkFormatProperties(ctypes.Structure):
    _fields_ = [
        ("linearTilingFeatures", ctypes.c_uint32),
        ("optimalTilingFeatures", ctypes.c_uint32),
        ("bufferFeatures", ctypes.c_uint32),
    ]


class VkExtent3D(ctypes.Structure):
    _fields_ = [("width", ctypes.c_uint32), ("height", ctypes.c_uint32), ("depth", ctypes.c_uint32)]


class VkImageFormatProperties(ctypes.Structure):
    _fields_ = [
        ("maxExtent", VkExtent3D),
        ("maxMipLevels", ctypes.c_uint32),
        ("maxArrayLayers", ctypes.c_uint32),
        ("sampleCounts", ctypes.c_uint32),
        ("maxResourceSize", ctypes.c_uint64),
    ]


def load():
    for name in ("libvulkan.so.1", "libvulkan.so"):
        try:
            return ctypes.CDLL(name)
        except OSError:
            continue
    raise SystemExit("no Vulkan loader found")


def device_name(vk, physical):
    buf = ctypes.create_string_buffer(512)
    # VkPhysicalDeviceProperties: apiVersion, driverVersion, vendorID, deviceID, deviceType,
    # then deviceName[256]. Only the name is read, so the tail of the struct is not declared.
    vk.vkGetPhysicalDeviceProperties(physical, ctypes.byref(buf))
    return buf.raw[20:20 + 256].split(b"\0")[0].decode("utf-8", "replace")


def main():
    vk = load()
    vk.vkCreateInstance.restype = ctypes.c_int
    vk.vkCreateInstance.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p)]
    vk.vkEnumeratePhysicalDevices.argtypes = [
        ctypes.c_void_p, ctypes.POINTER(ctypes.c_uint32), ctypes.POINTER(ctypes.c_void_p)]
    vk.vkGetPhysicalDeviceProperties.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    vk.vkGetPhysicalDeviceFormatProperties.argtypes = [
        ctypes.c_void_p, ctypes.c_int, ctypes.POINTER(VkFormatProperties)]
    vk.vkGetPhysicalDeviceImageFormatProperties.argtypes = [
        ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_uint32, ctypes.c_uint32,
        ctypes.POINTER(VkImageFormatProperties)]

    app = VkApplicationInfo(VK_STRUCTURE_TYPE_APPLICATION_INFO, None, b"celtium-depth-probe", 1,
                            b"celtium-depth-probe", 1, (1 << 22))
    create = VkInstanceCreateInfo(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, None, 0,
                                  ctypes.cast(ctypes.byref(app), ctypes.c_void_p), 0, None, 0, None)
    instance = ctypes.c_void_p()
    result = vk.vkCreateInstance(ctypes.byref(create), None, ctypes.byref(instance))
    if result != VK_SUCCESS:
        raise SystemExit("vkCreateInstance failed: %d" % result)

    count = ctypes.c_uint32(0)
    vk.vkEnumeratePhysicalDevices(instance, ctypes.byref(count), None)
    devices = (ctypes.c_void_p * count.value)()
    vk.vkEnumeratePhysicalDevices(instance, ctypes.byref(count), devices)

    icds = os.environ.get("VK_ICD_FILENAMES") or os.environ.get("VK_DRIVER_FILES") or "(loader default)"
    print("ICD: %s" % icds)
    for index in range(count.value):
        physical = devices[index]
        print("device %d: %s" % (index, device_name(vk, physical)))
        print("  %-22s %-10s %-8s %-8s %-8s %s" % (
            "format", "depthSten", "sampled", "linear", "create", "sampleCounts/mips"))
        for name, fmt, has_stencil in CANDIDATES:
            props = VkFormatProperties()
            vk.vkGetPhysicalDeviceFormatProperties(physical, fmt, ctypes.byref(props))
            # A stencil-only format has no depth aspect at all, so it cannot back a depth attachment.
            attachment = bool(props.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
            sampled = bool(props.optimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)
            linear = bool(props.linearTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)
            image = VkImageFormatProperties()
            create = vk.vkGetPhysicalDeviceImageFormatProperties(
                physical, fmt, VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, 0,
                ctypes.byref(image))
            detail = "result=%d" % create
            if create == VK_SUCCESS:
                detail = "1x1=%s mips=%d" % (
                    "yes" if image.sampleCounts & 1 else "no", image.maxMipLevels)
            print("  %-22s %-10s %-8s %-8s %-8s %s" % (
                name, "yes" if attachment else "NO", "yes" if sampled else "NO",
                "yes" if linear else "NO",
                "ok" if create == VK_SUCCESS else "NO(%d)" % create, detail))
    vk.vkDestroyInstance(instance, None)
    print("")


if __name__ == "__main__":
    main()
