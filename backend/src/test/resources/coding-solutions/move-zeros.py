import sys
d = sys.stdin.read().split()
a = d[1:1 + int(d[0])]
nz = [x for x in a if int(x) != 0]
print(" ".join(nz + ["0"] * (len(a) - len(nz))))
