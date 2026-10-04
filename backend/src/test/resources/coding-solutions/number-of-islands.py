import sys
from collections import deque
d = sys.stdin.read().split()
r, c = int(d[0]), int(d[1])
g = [bytearray(row, "ascii") for row in d[2:2 + r]]
n = 0
for i in range(r):
    for j in range(c):
        if g[i][j] == 49:
            n += 1
            g[i][j] = 48
            q = deque([(i, j)])
            while q:
                x, y = q.popleft()
                for a, b in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= a < r and 0 <= b < c and g[a][b] == 49:
                        g[a][b] = 48
                        q.append((a, b))
print(n)
