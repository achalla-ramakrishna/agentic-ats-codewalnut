import sys, bisect
d = sys.stdin.read().split()
n, q = int(d[0]), int(d[1])
a = list(map(int, d[2:2 + n]))
out = []
for x in map(int, d[2 + n:2 + n + q]):
    i = bisect.bisect_left(a, x)
    out.append(str(i if i < n and a[i] == x else -1))
print("\n".join(out))
