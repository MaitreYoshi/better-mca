import socket, struct, sys, time

sys.stdout.reconfigure(encoding="utf-8")

def pkt(i, t, body):
    b = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, i, t) + b

def read_exact(s, n):
    data = b""
    while len(data) < n:
        chunk = s.recv(n - len(data))
        if not chunk:
            raise EOFError
        data += chunk
    return data

def recv_all(s):
    # une réponse longue arrive en plusieurs paquets : on lit jusqu'au silence
    out = b""
    s.settimeout(1.0)
    try:
        while True:
            n = struct.unpack("<i", read_exact(s, 4))[0]
            data = read_exact(s, n)
            out += data[8:-2]
    except (socket.timeout, EOFError):
        pass
    s.settimeout(None)
    return out.decode("utf-8", "replace")

s = socket.create_connection(("127.0.0.1", 25599))
s.sendall(pkt(1, 3, "devtest")); recv_all(s)
for cmd in sys.argv[1:]:
    if cmd.startswith("sleep "):
        time.sleep(float(cmd[6:])); continue
    s.sendall(pkt(2, 2, cmd))
    print(">", cmd)
    print(recv_all(s))
    print()
