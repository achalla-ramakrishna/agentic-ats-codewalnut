import sys
d = sys.stdin.read().split()
a = d[1:1 + int(d[0])]
out = []
for x in a:
    if not out or out[-1] != x:
        out.append(x)
print(len(out))
print(" ".join(out))
