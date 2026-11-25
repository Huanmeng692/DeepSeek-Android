package cn.drdream.deepseek_accountlogin.ui.markdown

/** Best-effort LaTeX to readable Unicode for inline and display formulas. */
object Latex {
    private val greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
        "Psi" to "Ψ", "Omega" to "Ω",
    )

    private val symbols = mapOf(
        "times" to "×", "cdot" to "·", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "propto" to "∝",
        "infty" to "∞", "sum" to "∑", "prod" to "∏", "int" to "∫",
        "iint" to "∬", "oint" to "∮", "partial" to "∂", "nabla" to "∇",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "supset" to "⊃",
        "subseteq" to "⊆", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔", "mapsto" to "↦", "uparrow" to "↑",
        "downarrow" to "↓", "forall" to "∀", "exists" to "∃",
        "emptyset" to "∅", "varnothing" to "∅", "therefore" to "∴",
        "because" to "∵", "ldots" to "…", "dots" to "…", "cdots" to "⋯",
        "vdots" to "⋮", "ddots" to "⋱", "angle" to "∠", "perp" to "⊥",
        "parallel" to "∥", "circ" to "∘", "degree" to "°",
        "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋",
        "lceil" to "⌈", "rceil" to "⌉", "star" to "⋆", "ast" to "∗",
    )

    private val superMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 't' to 'ᵗ', 'x' to 'ˣ', 'a' to 'ᵃ',
        'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'k' to 'ᵏ',
        'm' to 'ᵐ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 'y' to 'ʸ',
    )

    private val subMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ', 'y' to 'ᵧ',
    )

    fun toUnicode(input: String): String {
        var result = input
        result = result.replace(Regex("""\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}"""), "($1)/($2)")
        result = result.replace(Regex("""\\dfrac\s*\{([^{}]*)\}\s*\{([^{}]*)\}"""), "($1)/($2)")
        result = result.replace(Regex("""\\sqrt\s*\[([^\]]*)\]\s*\{([^{}]*)\}"""), "($1)√($2)")
        result = result.replace(Regex("""\\sqrt\s*\{([^{}]*)\}"""), "√($1)")
        result = result.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\s*\{([^{}]*)\}"""), "$1")
        result = result.replace(Regex("""\\(?:left|right|big|Big|bigg|Bigg)\b"""), "")
        result = result.replace("\\,", " ").replace("\\;", " ").replace("\\!", "")
        result = result.replace("\\quad", "  ").replace("\\qquad", "   ")
        result = result.replace("\\{", "{").replace("\\}", "}")
        result = result.replace("\\\\", " ; ")

        for ((name, char) in greek) {
            result = result.replace("\\$name", char)
        }
        for ((name, char) in symbols) {
            result = result.replace("\\$name", char)
        }

        result = result.replace(Regex("""\^\{([^{}]*)\}""")) { superscript(it.groupValues[1]) }
        result = result.replace(Regex("""_\{([^{}]*)\}""")) { subscript(it.groupValues[1]) }
        result = result.replace(Regex("""\^(\w)""")) { superscript(it.groupValues[1]) }
        result = result.replace(Regex("""_(\w)""")) { subscript(it.groupValues[1]) }
        result = result.replace(Regex("""\\([A-Za-z]+)"""), "$1")
        return result.trim()
    }

    private fun superscript(text: String): String {
        val mapped = buildString {
            for (char in text) append(superMap[char] ?: return "^($text)")
        }
        return mapped
    }

    private fun subscript(text: String): String {
        val mapped = buildString {
            for (char in text) append(subMap[char] ?: return "_($text)")
        }
        return mapped
    }
}
