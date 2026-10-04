import sys
from collections import Counter
d = sys.stdin.read().split()
n, k = int(d[0]), int(d[1])
c = Counter(map(int, d[2:2 + n]))
print(" ".join(map(str, sorted(c, key=lambda x: (-c[x], x))[:k])))
