import sys
from collections import defaultdict
d = sys.stdin.read().split()
n, k = int(d[0]), int(d[1])
seen = defaultdict(int); seen[0] = 1
s = total = 0
for x in map(int, d[2:2 + n]):
    s += x
    total += seen[s - k]
    seen[s] += 1
print(total)
