import sys
from collections import Counter
d = sys.stdin.read().split()
a = list(map(int, d[1:1 + int(d[0])]))
c = Counter(a)
print(" ".join(map(str, sorted(a, key=lambda x: (-c[x], x)))))
