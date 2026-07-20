import socket, struct, json, sys
host, port = sys.argv[1], int(sys.argv[2])
def varint(n):
    b=b""
    while True:
        x=n&0x7F; n>>=7
        b+=bytes([x|(0x80 if n else 0)])
        if not n: return b
def rvarint(s):
    n=0; shift=0
    while True:
        d=s.recv(1)
        if not d: raise ConnectionError("closed")
        b=d[0]; n|=(b&0x7F)<<shift; shift+=7
        if not b&0x80: return n
s=socket.create_connection((host,port),timeout=10); s.settimeout(10)
hs=b"\x00"+varint(770)+varint(len(host))+host.encode()+struct.pack(">H",port)+varint(1)
s.sendall(varint(len(hs))+hs)
s.sendall(varint(1)+b"\x00")
rvarint(s); pid=rvarint(s); ln=rvarint(s)
buf=b""
while len(buf)<ln:
    c=s.recv(ln-len(buf))
    if not c: break
    buf+=c
d=json.loads(buf.decode("utf8",errors="replace"))
print("MOTD:", json.dumps(d.get("description"))[:80])
print("VERSION:", d.get("version",{}).get("name"), "| protocol", d.get("version",{}).get("protocol"))
print("PLAYERS:", d.get("players",{}).get("online"), "/", d.get("players",{}).get("max"))
