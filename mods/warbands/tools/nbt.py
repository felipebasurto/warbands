"""Tiny NBT reader/writer (enough for structure files). No dependencies."""
import gzip, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Int(int):
    pass


def _tag_of(v):
    if isinstance(v, bool): return BYTE
    if isinstance(v, int): return INT
    if isinstance(v, float): return DOUBLE
    if isinstance(v, str): return STRING
    if isinstance(v, list): return LIST
    if isinstance(v, dict): return COMPOUND
    raise TypeError(type(v))


def _write(t, v, out):
    if t == BYTE: out += struct.pack('>b', int(v))
    elif t == SHORT: out += struct.pack('>h', v)
    elif t == INT: out += struct.pack('>i', v)
    elif t == LONG: out += struct.pack('>q', v)
    elif t == FLOAT: out += struct.pack('>f', v)
    elif t == DOUBLE: out += struct.pack('>d', v)
    elif t == STRING:
        b = v.encode('utf-8'); out += struct.pack('>H', len(b)) + b
    elif t == LIST:
        et = _tag_of(v[0]) if v else END
        out += struct.pack('>bi', et, len(v))
        for e in v: _write(et, e, out)
    elif t == COMPOUND:
        for k, e in v.items():
            et = _tag_of(e); out += struct.pack('>b', et); _write(STRING, k, out); _write(et, e, out)
        out += b'\x00'


def write_file(path, root):
    out = bytearray(struct.pack('>b', COMPOUND)); _write(STRING, '', out); _write(COMPOUND, root, out)
    with gzip.open(path, 'wb') as f: f.write(bytes(out))


def read_file(path):
    data = gzip.open(path).read(); pos = [0]
    def rd(fmt):
        s = struct.calcsize(fmt); r = struct.unpack_from(fmt, data, pos[0]); pos[0] += s; return r[0]
    def val(t):
        if t == BYTE: return rd('>b')
        if t == SHORT: return rd('>h')
        if t == INT: return rd('>i')
        if t == LONG: return rd('>q')
        if t == FLOAT: return rd('>f')
        if t == DOUBLE: return rd('>d')
        if t == STRING:
            n = rd('>H'); s = data[pos[0]:pos[0] + n].decode(); pos[0] += n; return s
        if t == LIST:
            et = rd('>b'); n = rd('>i'); return [val(et) for _ in range(n)]
        if t == COMPOUND:
            d = {}
            while True:
                et = rd('>b')
                if et == END: return d
                k = val(STRING); d[k] = val(et)
        if t in (BYTE_ARRAY, INT_ARRAY, LONG_ARRAY):
            n = rd('>i'); f = {BYTE_ARRAY: '>b', INT_ARRAY: '>i', LONG_ARRAY: '>q'}[t]; return [rd(f) for _ in range(n)]
    rd('>b'); val(STRING); return val(COMPOUND)
