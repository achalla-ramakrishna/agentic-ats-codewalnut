import sys, heapq
d = sys.stdin.read().split()
n = int(d[0])
lo = []  # max-heap (negated): the smaller half, holds the median
hi = []  # min-heap: the larger half
out = []
for x in map(int, d[1:1 + n]):
    if not lo or x <= -lo[0]:
        heapq.heappush(lo, -x)
    else:
        heapq.heappush(hi, x)
    if len(lo) > len(hi) + 1:
        heapq.heappush(hi, -heapq.heappop(lo))
    elif len(hi) > len(lo):
        heapq.heappush(lo, -heapq.heappop(hi))
    out.append(-lo[0])
print(" ".join(map(str, out)))
