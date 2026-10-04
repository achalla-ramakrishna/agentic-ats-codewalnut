import sys
d = sys.stdin.read().split()
n = int(d[0])
iv = sorted(((int(d[1 + 2 * i]), int(d[2 + 2 * i])) for i in range(n)), key=lambda x: x[1])
count, last = 0, -1
for s, e in iv:
    if s >= last:
        count += 1
        last = e
print(count)
