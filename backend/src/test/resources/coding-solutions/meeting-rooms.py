import sys
d = sys.stdin.read().split()
n = int(d[0])
ev = []
for i in range(n):
    ev.append((int(d[1 + 2 * i]), 1)); ev.append((int(d[2 + 2 * i]), -1))
ev.sort()
cur = best = 0
for _, k in ev:
    cur += k
    best = max(best, cur)
print(best)
