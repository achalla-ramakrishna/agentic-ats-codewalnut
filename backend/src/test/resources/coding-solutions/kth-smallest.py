import sys
d = sys.stdin.read().split()
n, k = int(d[0]), int(d[1])
print(sorted(map(int, d[2:2 + n]))[k - 1])
