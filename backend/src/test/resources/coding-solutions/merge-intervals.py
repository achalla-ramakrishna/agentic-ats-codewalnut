import sys
d = sys.stdin.read().split()
n = int(d[0])
iv = sorted((int(d[1 + 2 * i]), int(d[2 + 2 * i])) for i in range(n))
out = []
for s, e in iv:
    if out and s <= out[-1][1]:
        out[-1][1] = max(out[-1][1], e)
    else:
        out.append([s, e])
print("\n".join("%d %d" % (s, e) for s, e in out))
