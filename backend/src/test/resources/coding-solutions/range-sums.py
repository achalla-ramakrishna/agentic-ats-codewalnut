import sys
d = sys.stdin.buffer.read().split()
n, q = int(d[0]), int(d[1])
p = [0] * (n + 1)
for i in range(n):
    p[i + 1] = p[i] + int(d[2 + i])
out = []
k = 2 + n
for _ in range(q):
    l, r = int(d[k]), int(d[k + 1]); k += 2
    out.append(str(p[r] - p[l - 1]))
print("\n".join(out))
