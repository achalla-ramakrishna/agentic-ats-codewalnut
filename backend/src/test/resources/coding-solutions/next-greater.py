import sys
d = sys.stdin.read().split()
n = int(d[0]); a = list(map(int, d[1:1 + n]))
res = [-1] * n; st = []
for i, x in enumerate(a):
    while st and a[st[-1]] < x:
        res[st.pop()] = x
    st.append(i)
print(" ".join(map(str, res)))
