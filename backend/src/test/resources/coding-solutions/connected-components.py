import sys
d = sys.stdin.read().split()
n, m = int(d[0]), int(d[1])
parent = list(range(n + 1))
def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x
comps = n
for i in range(m):
    a, b = find(int(d[2 + 2 * i])), find(int(d[3 + 2 * i]))
    if a != b:
        parent[a] = b
        comps -= 1
print(comps)
