using System;
using System.IO;
using System.Linq;
using Mono.Cecil;

internal static class Program
{
    private static readonly string[] Keywords =
    {
        "weapon", "revolver", "gun", "shoot", "fire", "projectile",
        "bullet", "input", "slot", "equip", "switch", "attack",
        "player", "weaponpick", "guncontrol", "newmovement"
    };

    private static int Main(string[] args)
    {
        if (args.Length != 1 || !File.Exists(args[0]))
        {
            Console.Error.WriteLine("Usage: inspector <path-to-Assembly-CSharp.dll>");
            return 2;
        }

        using (AssemblyDefinition assembly = AssemblyDefinition.ReadAssembly(args[0]))
        using (var output = new StreamWriter("weapon-api-report.txt", false))
        {
            output.WriteLine("TWIN REQUIEM game API inspection");
            output.WriteLine("Assembly: " + assembly.Name.FullName);
            output.WriteLine();

            foreach (TypeDefinition type in assembly.MainModule.Types
                .SelectMany(Flatten)
                .Where(t => Keywords.Any(k => t.FullName.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)
                    || t.Methods.Any(m => Keywords.Any(k => m.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)))
                .OrderBy(t => t.FullName))
            {
                output.WriteLine("TYPE " + type.FullName);
                foreach (MethodDefinition method in type.Methods
                    .Where(m => Keywords.Any(k => m.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)
                        || type.FullName.IndexOf("weapon", StringComparison.OrdinalIgnoreCase) >= 0
                        || type.FullName.IndexOf("revolver", StringComparison.OrdinalIgnoreCase) >= 0
                        || type.FullName.IndexOf("gun", StringComparison.OrdinalIgnoreCase) >= 0)
                    .OrderBy(m => m.Name))
                {
                    string parameters = string.Join(", ", method.Parameters.Select(p => p.ParameterType.FullName + " " + p.Name));
                    output.WriteLine("  " + (method.IsPublic ? "public " : method.IsFamily ? "protected " : "nonpublic ")
                        + (method.IsStatic ? "static " : "")
                        + method.ReturnType.FullName + " " + method.Name + "(" + parameters + ")");
                }
                output.WriteLine();
            }
        }

        Console.WriteLine("Wrote weapon-api-report.txt");
        return 0;
    }

    private static System.Collections.Generic.IEnumerable<TypeDefinition> Flatten(TypeDefinition type)
    {
        yield return type;
        foreach (TypeDefinition nested in type.NestedTypes)
            foreach (TypeDefinition child in Flatten(nested))
                yield return child;
    }
}
