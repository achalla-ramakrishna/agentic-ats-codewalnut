import sys
from itertools import combinations
d = sys.stdin.read().split()
a = sorted(map(int, d[1:1 + int(d[0])]))
subs = [list(c) for r in range(len(a) + 1) for c in combinations(a, r)]
subs.sort()
print("\n".join(" ".join(map(str, s)) if s else "-" for s in subs))
