import sys
d = sys.stdin.read().split()
n = int(d[0]); h = list(map(int, d[1:1 + n]))
i, j, best = 0, n - 1, 0
while i < j:
    best = max(best, (j - i) * min(h[i], h[j]))
    if h[i] < h[j]:
        i += 1
    else:
        j -= 1
print(best)
