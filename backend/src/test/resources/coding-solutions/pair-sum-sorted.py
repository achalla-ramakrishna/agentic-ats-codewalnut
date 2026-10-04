import sys
d = sys.stdin.read().split()
n, t = int(d[0]), int(d[1])
a = list(map(int, d[2:2 + n]))
i, j = 0, n - 1
ok = False
while i < j:
    s = a[i] + a[j]
    if s == t:
        ok = True; break
    if s < t:
        i += 1
    else:
        j -= 1
print("YES" if ok else "NO")
