import sys
d = sys.stdin.read().split()
n = int(d[0])
far = 0
for i in range(n):
    if i > far:
        break
    far = max(far, i + int(d[1 + i]))
print("YES" if far >= n - 1 else "NO")
