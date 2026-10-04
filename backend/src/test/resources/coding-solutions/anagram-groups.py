import sys
from collections import Counter
d = sys.stdin.read().split()
c = Counter("".join(sorted(w)) for w in d[1:1 + int(d[0])])
print(len(c), max(c.values()))
