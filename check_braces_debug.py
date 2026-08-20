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
                else:
                    pass
        if i + 1 == 1040:
            print(f"Stack size at line 1040: {len(stack)}")
        if i + 1 == 1180:
            print(f"Stack size at line 1180: {len(stack)}")
                    
    if stack:
        print(f"Unclosed braces opened at lines: {stack}")
        
check_braces('app/src/main/java/com/example/MainActivity.kt')
