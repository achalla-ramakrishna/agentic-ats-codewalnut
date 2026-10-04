import sys
d = sys.stdin.read().split()
n, t = int(d[0]), int(d[1])
ways = [1] + [0] * t
for x in map(int, d[2:2 + n]):
    for s in range(x, t + 1):
        ways[s] += ways[s - x]
print(ways[t])
