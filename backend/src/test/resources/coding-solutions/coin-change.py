import sys
d = sys.stdin.read().split()
n, a = int(d[0]), int(d[1])
coins = list(map(int, d[2:2 + n]))
INF = a + 1
best = [0] + [INF] * a
for x in range(1, a + 1):
    for c in coins:
        if c <= x and best[x - c] + 1 < best[x]:
            best[x] = best[x - c] + 1
print(best[a] if best[a] < INF else -1)
