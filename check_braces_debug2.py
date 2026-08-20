import sys

def check_braces(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
        
    stack = []
    for i, line in enumerate(lines):
        for char in line:
            if char == '{':
                stack.append(i + 1)
            elif char == '}':
                if stack:
                    stack.pop()
        if i + 1 in [100, 200, 300, 400, 500, 600, 700, 800, 900, 910, 920, 930]:
            print(f"Stack size at line {i + 1}: {len(stack)}")
                    
check_braces('app/src/main/java/com/example/MainActivity.kt')
