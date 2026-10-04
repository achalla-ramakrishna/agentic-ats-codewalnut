import sys
d = sys.stdin.read().split()
n = int(d[0])
best = cur = None
for x in map(int, d[1:1 + n]):
    cur = x if cur is None or cur < 0 else cur + x
    best = cur if best is None else max(best, cur)
print(best)
