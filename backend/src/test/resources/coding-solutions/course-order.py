import sys, heapq
d = sys.stdin.read().split()
n, m = int(d[0]), int(d[1])
adj = [[] for _ in range(n + 1)]
indeg = [0] * (n + 1)
for i in range(m):
    a, b = int(d[2 + 2 * i]), int(d[3 + 2 * i])
    adj[a].append(b); indeg[b] += 1
h = [v for v in range(1, n + 1) if indeg[v] == 0]
heapq.heapify(h)
out = []
while h:
    v = heapq.heappop(h); out.append(v)
    for w in adj[v]:
        indeg[w] -= 1
        if indeg[w] == 0:
            heapq.heappush(h, w)
print(" ".join(map(str, out)) if len(out) == n else -1)
