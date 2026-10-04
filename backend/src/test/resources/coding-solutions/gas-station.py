import sys
d = sys.stdin.read().split()
n = int(d[0])
g = list(map(int, d[1:1 + n])); c = list(map(int, d[1 + n:1 + 2 * n]))
if sum(g) < sum(c):
    print(-1)
else:
    start = tank = 0
    for i in range(n):
        tank += g[i] - c[i]
        if tank < 0:
            start = i + 1
            tank = 0
    print(start)
