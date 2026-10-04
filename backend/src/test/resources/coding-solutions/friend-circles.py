import sys
d = sys.stdin.read().split()
n, q = int(d[0]), int(d[1])
p = list(range(n + 1))
def find(x):
    while p[x] != x:
        p[x] = p[p[x]]
        x = p[x]
    return x
out = []
i = 2
for _ in range(q):
    op, a, b = d[i], int(d[i + 1]), int(d[i + 2]); i += 3
    if op == "union":
        p[find(a)] = find(b)
    else:
        out.append("YES" if find(a) == find(b) else "NO")
print("\n".join(out))
