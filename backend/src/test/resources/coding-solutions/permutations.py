import sys
from itertools import permutations
d = sys.stdin.read().split()
a = sorted(map(int, d[1:1 + int(d[0])]))
print("\n".join(" ".join(map(str, p)) for p in permutations(a)))
