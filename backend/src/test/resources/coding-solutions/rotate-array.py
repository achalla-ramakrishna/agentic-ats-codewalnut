import sys
d = sys.stdin.read().split()
n, k = int(d[0]), int(d[1])
a = d[2:2 + n]
k %= n
print(" ".join(a[n - k:] + a[:n - k]))
