import sys
d = sys.stdin.read().split()
n, q = int(d[0]), int(d[1])
a = list(map(int, d[2:2 + n]))
pos = {x: i for i, x in enumerate(a)}
print("\n".join(str(pos.get(int(x), -1)) for x in d[2 + n:2 + n + q]))
