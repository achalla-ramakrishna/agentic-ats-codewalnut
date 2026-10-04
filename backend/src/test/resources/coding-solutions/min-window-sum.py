import sys
d = sys.stdin.read().split()
n, s = int(d[0]), int(d[1])
a = list(map(int, d[2:2 + n]))
best = 0; total = 0; i = 0
for j in range(n):
    total += a[j]
    while total >= s:
        best = j - i + 1 if best == 0 else min(best, j - i + 1)
        total -= a[i]; i += 1
print(best)
