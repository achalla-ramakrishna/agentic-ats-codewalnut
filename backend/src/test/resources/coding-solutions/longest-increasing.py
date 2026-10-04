import sys, bisect
d = sys.stdin.read().split()
tails = []
for x in map(int, d[1:1 + int(d[0])]):
    i = bisect.bisect_left(tails, x)
    if i == len(tails):
        tails.append(x)
    else:
        tails[i] = x
print(len(tails))
