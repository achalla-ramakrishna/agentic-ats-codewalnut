import sys
d = sys.stdin.read().split()
n, m = int(d[0]), int(d[1])
g = sorted(map(int, d[2:2 + n])); s = sorted(map(int, d[2 + n:2 + n + m]))
i = 0
for x in s:
    if i < n and x >= g[i]:
        i += 1
print(i)
