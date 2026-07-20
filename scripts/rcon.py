#!/usr/bin/env python3
"""Minimal Source-RCON client for driving a Paper console during gate 7a.

Usage: rcon.py <host> <port> <password> <command> [<command> ...]
Each command is sent in order; each response is printed prefixed by the command.
Exit status is non-zero if authentication fails.
"""
import socket
import struct
import sys

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


def pack(req_id: int, req_type: int, body: str) -> bytes:
    payload = struct.pack("<ii", req_id, req_type) + body.encode("utf8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("socket closed mid-packet")
        buf += chunk
    return buf


def read_packet(sock: socket.socket):
    (length,) = struct.unpack("<i", read_exact(sock, 4))
    payload = read_exact(sock, length)
    req_id, req_type = struct.unpack("<ii", payload[:8])
    body = payload[8:-2].decode("utf8", errors="replace")
    return req_id, req_type, body


def main() -> int:
    host, port, password = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    commands = sys.argv[4:]

    with socket.create_connection((host, port), timeout=15) as sock:
        sock.settimeout(15)
        sock.sendall(pack(1, SERVERDATA_AUTH, password))
        req_id, req_type, _ = read_packet(sock)
        # Some servers emit an empty RESPONSE_VALUE before the auth result.
        if req_type == SERVERDATA_RESPONSE_VALUE:
            req_id, req_type, _ = read_packet(sock)
        if req_id == -1:
            print("AUTH FAILED", file=sys.stderr)
            return 1
        print("AUTH OK")

        for i, cmd in enumerate(commands, start=2):
            sock.sendall(pack(i, SERVERDATA_EXECCOMMAND, cmd))
            _, _, body = read_packet(sock)
            print(f"$ {cmd}\n{body.strip()}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
