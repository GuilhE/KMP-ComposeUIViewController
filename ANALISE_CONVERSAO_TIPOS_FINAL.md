# Análise e Correção da Conversão de Tipos Kotlin ↔ Swift

## Problema Reportado

Ao gerar código Swift a partir de funções Composable anotadas com `@ComposeUIViewController`, tipos primitivos Kotlin estavam sendo convertidos incorretamente:

**Exemplo do Problema:**
```swift
public struct TestRepresentable: UIViewControllerRepresentable {
    let v1: KotlinBoolean     // ❌ ERRADO - deveria ser Bool
    let v2: (KotlinBoolean) -> Void  // ✅ CORRETO
}
```

## Solução: Conversão Sensível ao Contexto

A solução implementada reconhece que **o mesmo tipo primitivo deve ser convertido diferentemente dependendo do contexto de uso**.

### Regras de Conversão por Contexto (ObjC Export)

#### 1. **Parâmetro Direto (não-nullable)**
Usa **tipo nativo Swift**

| Kotlin | Swift | Exemplo |
|--------|-------|---------|
| `value: Int` | `let value: Int32` | Parâmetro direto da struct |
| `flag: Boolean` | `let flag: Bool` | Parâmetro direto da struct |
| `byte: Byte` | `let byte: Int8` | Parâmetro direto da struct |
| `text: String` | `let text: String` | String não precisa wrapper |

**Exemplo Completo:**
```kotlin
@ComposeUIViewController
@Composable
fun Screen(value: Int, flag: Boolean) { }
```

Gera:
```swift
public struct ScreenRepresentable: UIViewControllerRepresentable {
    let value: Int32      // ✅ Tipo nativo
    let flag: Bool        // ✅ Tipo nativo
}
```

#### 2. **Dentro de Closure/Função**
Usa **wrapper Kotlin**

| Kotlin | Swift | Exemplo |
|--------|-------|---------|
| `callback: (Int) -> Unit` | `let callback: (KotlinInt) -> Void` | Int dentro de função |
| `onToggle: (Boolean) -> Unit` | `let onToggle: (KotlinBoolean) -> Void` | Boolean dentro de função |
| `handler: (Int, Boolean) -> String` | `let handler: (KotlinInt, KotlinBoolean) -> String` | Múltiplos parâmetros |

**Exemplo Completo:**
```kotlin
@ComposeUIViewController
@Composable
fun Screen(
    value: Int,                    // Parâmetro direto
    callback: (Int) -> Unit        // Int dentro de função
) { }
```

Gera:
```swift
public struct ScreenRepresentable: UIViewControllerRepresentable {
    let value: Int32                        // ✅ Tipo nativo (direto)
    let callback: (KotlinInt) -> Void       // ✅ Wrapper (dentro de função)
}
```

#### 3. **Dentro de Tipo Genérico Customizado**
Usa **wrapper Kotlin**

| Kotlin | Swift | Exemplo |
|--------|-------|---------|
| `data: GenericData<Int>` | `let data: GenericData<KotlinInt>` | Int em tipo genérico |
| `result: Result<Boolean>` | `let result: Result<KotlinBoolean>` | Boolean em tipo genérico |
| `container: Box<Long>` | `let container: Box<KotlinLong>` | Long em tipo genérico |

**Exemplo Completo:**
```kotlin
data class GenericData<T>(val value: T)

@ComposeUIViewController
@Composable
fun Screen(
    value: Int,                    // Parâmetro direto
    data: GenericData<Int>         // Int dentro de genérico
) { }
```

Gera:
```swift
public struct ScreenRepresentable: UIViewControllerRepresentable {
    let value: Int32                    // ✅ Tipo nativo (direto)
    let data: GenericData<KotlinInt>    // ✅ Wrapper (dentro de genérico)
}
```

#### 4. **Dentro de Coleções**
Usa **wrapper Kotlin** (exceto String e Char)

| Kotlin | Swift | Exemplo |
|--------|-------|---------|
| `numbers: List<Int>` | `let numbers: Array<KotlinInt>` | Int em coleção |
| `flags: Set<Boolean>` | `let flags: Set<KotlinBoolean>` | Boolean em coleção |
| `names: List<String>` | `let names: Array<String>` | ⭐ String não precisa wrapper |
| `chars: List<Char>` | `let chars: Array<Any>` | ⭐ Char vira Any em coleções |

#### 5. **Nullable (qualquer contexto)**
Sempre usa **wrapper Kotlin** (exceto String)

| Kotlin | Swift | Exemplo |
|--------|-------|---------|
| `value: Int?` | `let value: KotlinInt?` | Nullable direto |
| `text: String?` | `let text: String?` | ⭐ String não precisa wrapper |
| `callback: (Int?) -> Unit` | `let callback: (KotlinInt?) -> Void` | Nullable em função |
| `data: GenericData<Int?>` | `let data: GenericData<KotlinInt?>` | Nullable em genérico |

## Implementação Técnica

### Parâmetro `insideFunctionType`

Foi adicionado um parâmetro booleano `insideFunctionType` que rastreia o contexto:

```kotlin
private fun convertGenericType(
    type: KSType, 
    toSwift: Boolean, 
    withSwiftExport: Boolean, 
    insideFunctionType: Boolean = false
): String
```

### Lógica de Decisão

```kotlin
private fun convertToSwiftFromObjcExport(
    baseType: String, 
    isNullable: Boolean = false, 
    insideFunctionType: Boolean = false
): String {
    return when (baseType) {
        "Int" -> when {
            isNullable -> "KotlinInt"          // Int? sempre usa wrapper
            insideFunctionType -> "KotlinInt"   // Dentro de função usa wrapper
            else -> "Int32"                     // Parâmetro direto usa nativo
        }
        "Boolean" -> when {
            isNullable -> "KotlinBoolean"
            insideFunctionType -> "KotlinBoolean"
            else -> "Bool"
        }
        // ... outros tipos primitivos
    }
}
```

### Propagação do Contexto

1. **Parâmetros Diretos:** `insideFunctionType = false`
```kotlin
convertGenericType(resolvedType, toSwift, withSwiftExport, insideFunctionType = false)
```

2. **Dentro de Funções:** `insideFunctionType = true`
```kotlin
resolvedType.arguments.dropLast(1).joinToString(", ") { arg ->
    convertGenericType(arg.type?.resolve(), toSwift, withSwiftExport, insideFunctionType = true)
}
```

3. **Dentro de Generics:** `insideFunctionType = true`
```kotlin
if (type.arguments.isNotEmpty()) {
    val generics = type.arguments.joinToString(", ") { arg ->
        convertGenericType(arg.type?.resolve(), toSwift, withSwiftExport, insideFunctionType = true)
    }
}
```

## Exemplos Completos

### Exemplo 1: Combinação de Contextos

**Kotlin:**
```kotlin
@ComposeUIViewController
@Composable
fun Screen(
    id: Int,                                    // Contexto 1: Direto
    value: Int?,                                // Contexto 2: Direto nullable
    callback: (Int) -> Unit,                    // Contexto 3: Dentro de função
    data: GenericData<Int>,                     // Contexto 4: Dentro de genérico
    numbers: List<Int>,                         // Contexto 5: Dentro de coleção
    optional: GenericData<Int?>                 // Contexto 6: Genérico + nullable
) { }
```

**Swift Gerado:**
```swift
public struct ScreenRepresentable: UIViewControllerRepresentable {
    let id: Int32                                   // ✅ Contexto 1: Nativo
    let value: KotlinInt?                           // ✅ Contexto 2: Wrapper (nullable)
    let callback: (KotlinInt) -> Void               // ✅ Contexto 3: Wrapper (função)
    let data: GenericData<KotlinInt>                // ✅ Contexto 4: Wrapper (genérico)
    let numbers: Array<KotlinInt>                   // ✅ Contexto 5: Wrapper (coleção)
    let optional: GenericData<KotlinInt?>           // ✅ Contexto 6: Wrapper (gen+null)
}
```

### Exemplo 2: Tipos Complexos

**Kotlin:**
```kotlin
@ComposeUIViewController
@Composable
fun Screen(
    callback: (List<Map<String, List<Int>>>) -> List<String>
) { }
```

**Swift Gerado:**
```swift
public struct ScreenRepresentable: UIViewControllerRepresentable {
    let callback: (Array<Dictionary<String, Array<KotlinInt>>>) -> Array<String>
    //                                             ^^^^^^^^^^
    //                                  Int dentro de coleções usa wrapper
}
```

## Exceções Especiais

### String
- **Nunca** precisa de wrapper, em nenhum contexto
- `String?` → `String?` (não `KotlinString?`)

### Char
- Standalone: `unichar`
- Nullable: `Any?`
- Em coleções: `Any`

## Swift Export vs ObjC Export

### Swift Export
**Todos os primitivos usam tipos nativos**, independente do contexto:

| Kotlin | Swift Export |
|--------|--------------|
| `Int` | `Int32` |
| `Boolean` | `Bool` |
| `(Int) -> Unit` | `(Int32) -> Void` |
| `List<Int>` | `Array<Int32>` |

### ObjC Export
**Primitivos usam wrappers dependendo do contexto** (conforme documentado acima)

## Resultado

✅ **Todos os 19 testes passaram!**

O código agora:
- Gera tipos nativos Swift para parâmetros diretos primitivos
- Usa wrappers Kotlin dentro de funções, generics e coleções
- Trata corretamente nullability em todos os contextos
- Mantém compatibilidade com ObjC Export e Swift Export

