import sys
from collections import deque
d = sys.stdin.read().split()
r, c = int(d[0]), int(d[1])
g = d[2:2 + r]
for i in range(r):
    for j in range(c):
        if g[i][j] == "S": s = (i, j)
        if g[i][j] == "E": e = (i, j)
dist = {s: 0}
q = deque([s])
while q:
    x, y = q.popleft()
    if (x, y) == e:
        break
    for a, b in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
        if 0 <= a < r and 0 <= b < c and g[a][b] != "#" and (a, b) not in dist:
            dist[(a, b)] = dist[(x, y)] + 1
            q.append((a, b))
print(dist.get(e, -1))
