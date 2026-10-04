import sys, heapq
d = sys.stdin.read().split()
n, m = int(d[0]), int(d[1])
adj = [[] for _ in range(n + 1)]
for i in range(m):
    adj[int(d[2 + 3 * i])].append((int(d[3 + 3 * i]), int(d[4 + 3 * i])))
dist = [-1] * (n + 1)
h = [(0, 1)]
while h:
    du, u = heapq.heappop(h)
    if dist[u] != -1:
        continue
    dist[u] = du
    for v, w in adj[u]:
        if dist[v] == -1:
            heapq.heappush(h, (du + w, v))
print(" ".join(map(str, dist[1:])))
