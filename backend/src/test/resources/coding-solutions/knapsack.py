import sys
d = sys.stdin.read().split()
n, w = int(d[0]), int(d[1])
best = [0] * (w + 1)
for i in range(n):
    wt, v = int(d[2 + 2 * i]), int(d[3 + 2 * i])
    for c in range(w, wt - 1, -1):
        if best[c - wt] + v > best[c]:
            best[c] = best[c - wt] + v
print(best[w])
