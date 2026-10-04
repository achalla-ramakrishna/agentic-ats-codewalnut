import sys
data = sys.stdin.read().split()
t = int(data[0])
out = []
for s in data[1:1 + t]:
    x = int(s)
    ok = x >= 2
    d = 2
    while ok and d * d <= x:
        if x % d == 0:
            ok = False
        d += 1
    out.append("YES" if ok else "NO")
print("\n".join(out))
