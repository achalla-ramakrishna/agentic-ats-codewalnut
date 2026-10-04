import sys
d = sys.stdin.read().split()
v = sorted(set(map(int, d[1:1 + int(d[0])])))
print(v[-2] if len(v) > 1 else -1)
