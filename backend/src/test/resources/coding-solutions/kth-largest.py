import sys, heapq
d = sys.stdin.read().split()
n, k = int(d[0]), int(d[1])
h = []; out = []
for x in map(int, d[2:2 + n]):
    if len(h) < k:
        heapq.heappush(h, x)
    elif x > h[0]:
        heapq.heapreplace(h, x)
    out.append(h[0] if len(h) == k else -1)
print(" ".join(map(str, out)))
