import sys
d = sys.stdin.read().split()
n = int(d[0]); h = list(map(int, d[1:1 + n])) + [0]
st = []; best = 0
for i, x in enumerate(h):
    start = i
    while st and st[-1][1] >= x:
        j, y = st.pop()
        best = max(best, y * (i - j))
        start = j
    st.append((start, x))
print(best)
