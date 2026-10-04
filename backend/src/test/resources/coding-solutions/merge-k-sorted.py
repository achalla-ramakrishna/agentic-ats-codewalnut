import sys
d = sys.stdin.read().split()
k = int(d[0]); i = 1; allv = []
for _ in range(k):
    l = int(d[i]); allv.extend(map(int, d[i + 1:i + 1 + l])); i += 1 + l
print(" ".join(map(str, sorted(allv))))
