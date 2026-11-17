"""Minimal Java class-file reader for the cpu-contract checks.

Reads exactly what the compatibility-surface check needs and nothing more: the constant pool
(class / member references and UTF-8 strings), the class header (this class, superclass,
interfaces), the declared fields and methods with their descriptors, and the
RuntimeVisibleAnnotations of the class, its fields and its methods. It never loads or executes
the classes it reads.

The format is JVMS chapter 4; the reader is strict about lengths it walks and raises
ValueError on anything it cannot parse, so a malformed input fails the check loudly instead of
silently producing a partial surface.
"""
from __future__ import annotations

import struct

# Constant pool tags (JVMS 4.4).
_CP_UTF8 = 1
_CP_CLASS = 7
_CP_STRING = 8
_CP_FIELDREF = 9
_CP_METHODREF = 10
_CP_INTERFACE_METHODREF = 11
_CP_NAME_AND_TYPE = 12
_CP_METHOD_HANDLE = 15
_CP_METHOD_TYPE = 16
_CP_DYNAMIC = 17
_CP_INVOKE_DYNAMIC = 18
_CP_MODULE = 19
_CP_PACKAGE = 20


class ClassFile:
    def __init__(self, data: bytes):
        self._data = data
        self._pos = 0
        magic = self._u4()
        if magic != 0xCAFEBABE:
            raise ValueError("not a class file")
        self._u2()  # minor
        self._u2()  # major
        self.constant_pool = self._read_constant_pool()
        self.access_flags = self._u2()
        self.this_class = self._class_name(self._u2())
        super_index = self._u2()
        self.super_class = self._class_name(super_index) if super_index else None
        self.interfaces = [self._class_name(self._u2()) for _ in range(self._u2())]
        self.fields = self._read_members()
        self.methods = self._read_members()
        self.annotations = []
        for _ in range(self._u2()):
            name = self._utf8(self._u2())
            length = self._u4()
            if name == "RuntimeVisibleAnnotations":
                self.annotations = self._read_annotations(self._pos, self._pos + length)
            self._pos += length

    # -- low-level readers -------------------------------------------------
    def _u1(self) -> int:
        value = self._data[self._pos]
        self._pos += 1
        return value

    def _u2(self) -> int:
        value = struct.unpack_from(">H", self._data, self._pos)[0]
        self._pos += 2
        return value

    def _u4(self) -> int:
        value = struct.unpack_from(">I", self._data, self._pos)[0]
        self._pos += 4
        return value

    def _take(self, count: int) -> bytes:
        value = self._data[self._pos:self._pos + count]
        self._pos += count
        return value

    # -- constant pool ------------------------------------------------------
    def _read_constant_pool(self) -> list:
        count = self._u2()
        pool: list = [None] * count
        index = 1
        while index < count:
            tag = self._u1()
            if tag == _CP_UTF8:
                length = self._u2()
                pool[index] = (tag, self._take(length).decode("utf-8", "replace"))
            elif tag in (_CP_CLASS, _CP_STRING, _CP_METHOD_TYPE, _CP_MODULE, _CP_PACKAGE):
                pool[index] = (tag, self._u2())
            elif tag in (_CP_FIELDREF, _CP_METHODREF, _CP_INTERFACE_METHODREF, _CP_NAME_AND_TYPE):
                pool[index] = (tag, self._u2(), self._u2())
            elif tag in (_CP_DYNAMIC, _CP_INVOKE_DYNAMIC):
                self._u2()
                pool[index] = (tag, self._u2())
            elif tag == _CP_METHOD_HANDLE:
                pool[index] = (tag, self._u1(), self._u2())
            elif tag in (3, 4):  # Integer, Float
                pool[index] = (tag, self._u4())
            elif tag in (5, 6):  # Long, Double: two slots
                self._take(8)
                pool[index] = (tag, None)
                index += 1
            elif tag == 2:  # reserved
                raise ValueError("reserved constant pool tag")
            else:
                raise ValueError(f"unknown constant pool tag {tag}")
            index += 1
        return pool

    def _utf8(self, index: int) -> str:
        entry = self.constant_pool[index]
        if entry is None or entry[0] != _CP_UTF8:
            raise ValueError(f"constant pool entry {index} is not UTF8")
        return entry[1]

    def _class_name(self, index: int) -> str:
        entry = self.constant_pool[index]
        if entry is None or entry[0] != _CP_CLASS:
            raise ValueError(f"constant pool entry {index} is not a class")
        return self._utf8(entry[1])

    def member_references(self):
        """Yield (kind, owner, name, descriptor) for every field/method reference in the pool.

        kind is "field", "method" or "interface-method". These are the references the class's
        own bytecode makes; they say nothing about references spelled only inside annotations
        (the extractor handles those separately).
        """
        for entry in self.constant_pool:
            if entry is None:
                continue
            tag = entry[0]
            if tag not in (_CP_FIELDREF, _CP_METHODREF, _CP_INTERFACE_METHODREF):
                continue
            owner = self._class_name(entry[1])
            name_and_type = self.constant_pool[entry[2]]
            name = self._utf8(name_and_type[1])
            descriptor = self._utf8(name_and_type[2])
            kind = {9: "field", 10: "method", 11: "interface-method"}[tag]
            yield kind, owner, name, descriptor

    def referenced_class_names(self):
        """Yield every class named by a Class constant in the pool."""
        for entry in self.constant_pool:
            if entry is not None and entry[0] == _CP_CLASS:
                yield self._utf8(entry[1])

    def utf8_strings(self):
        """Yield every UTF-8 string in the pool (annotation payloads live here as text)."""
        for entry in self.constant_pool:
            if entry is not None and entry[0] == _CP_UTF8:
                yield entry[1]

    # -- members and annotations -------------------------------------------
    def _read_members(self) -> list:
        members = []
        for _ in range(self._u2()):
            access = self._u2()
            name = self._utf8(self._u2())
            descriptor = self._utf8(self._u2())
            annotations = []
            for _ in range(self._u2()):
                attr_name = self._utf8(self._u2())
                length = self._u4()
                if attr_name == "RuntimeVisibleAnnotations":
                    annotations = self._read_annotations(self._pos, self._pos + length)
                self._pos += length
            members.append({
                "access": access,
                "name": name,
                "descriptor": descriptor,
                "annotations": annotations,
            })
        return members

    def _read_annotations(self, start: int, end: int) -> list:
        # The caller keeps its own cursor and skips the whole attribute by length afterwards,
        # so this reader may run the cursor forward from the attribute body and restore it.
        saved = self._pos
        self._pos = start
        try:
            annotations = [self._read_annotation() for _ in range(self._u2())]
            if self._pos > end:
                raise ValueError("annotation attribute overruns its declared length")
        finally:
            self._pos = saved
        return annotations

    def _read_annotation(self) -> dict:
        type_name = self._utf8(self._u2())
        pairs = {}
        for _ in range(self._u2()):
            element_name = self._utf8(self._u2())
            pairs[element_name] = self._read_element_value()
        return {"type": type_name, "values": pairs}

    def _read_element_value(self):
        tag = chr(self._u1())
        if tag in "BCDFIJSZs":
            return self._utf8(self._u2()) if tag == "s" else self._u2()
        if tag == "e":
            return ("enum", self._utf8(self._u2()), self._utf8(self._u2()))
        if tag == "c":
            return self._utf8(self._u2())
        if tag == "@":
            return self._read_annotation()
        if tag == "[":
            return [self._read_element_value() for _ in range(self._u2())]
        raise ValueError(f"unknown annotation element tag {tag!r}")
