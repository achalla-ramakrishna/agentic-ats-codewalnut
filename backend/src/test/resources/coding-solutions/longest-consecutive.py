import sys
d = sys.stdin.read().split()
s = set(map(int, d[1:1 + int(d[0])]))
best = 0
for x in s:
    if x - 1 not in s:
        y = x
        while y + 1 in s:
            y += 1
        best = max(best, y - x + 1)
print(best)
