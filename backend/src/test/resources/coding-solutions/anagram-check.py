from collections import Counter
a = input().strip(); b = input().strip()
print("YES" if Counter(a) == Counter(b) else "NO")
