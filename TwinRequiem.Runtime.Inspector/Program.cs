using System;
using System.IO;
using System.Linq;
using Mono.Cecil;

internal static class Program
{
    private static readonly string[] Keywords =
    {
        "whiplash", "hookarm", "grapple", "hook", "enemyidentifier",
        "enemy", "damage", "hurt", "collision", "rigidbody", "newmovement",
        "playercamera", "camera", "input", "weapon", "attack", "fire"
    };

    private static readonly string[] DeepTypes =
    {
        "whiplash", "hookarm", "grapple", "enemyidentifier", "newmovement",
        "playercamera", "hook"
    };

    private static int Main(string[] args)
    {
        if (args.Length != 1 || !File.Exists(args[0]))
        {
            Console.Error.WriteLine("Usage: inspector <path-to-Assembly-CSharp.dll>");
            return 2;
        }

        using (AssemblyDefinition assembly = AssemblyDefinition.ReadAssembly(args[0]))
        using (var output = new StreamWriter("whiplash-api-report.txt", false))
        {
            output.WriteLine("ULTRAKILL Whiplash / enemy / physics API inspection");
            output.WriteLine("Assembly: " + assembly.Name.FullName);
            output.WriteLine("Source file: " + Path.GetFullPath(args[0]));
            output.WriteLine();

            foreach (TypeDefinition type in assembly.MainModule.Types
                .SelectMany(Flatten)
                .Where(t => Keywords.Any(k => t.FullName.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)
                    || t.Methods.Any(m => Keywords.Any(k => m.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)))
                .OrderBy(t => t.FullName))
            {
                bool deep = DeepTypes.Any(k => type.FullName.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0);
                output.WriteLine("TYPE " + type.FullName);
                foreach (FieldDefinition field in type.Fields.Where(f => deep ||
                    Keywords.Any(k => f.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)).OrderBy(f => f.Name))
                    output.WriteLine("  FIELD " + (field.IsPublic ? "public " : "nonpublic ") +
                        (field.IsStatic ? "static " : "") + field.FieldType.FullName + " " + field.Name);

                foreach (PropertyDefinition property in type.Properties.Where(p => deep ||
                    Keywords.Any(k => p.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0)).OrderBy(p => p.Name))
                    output.WriteLine("  PROPERTY " + property.PropertyType.FullName + " " + property.Name);

                foreach (MethodDefinition method in type.Methods.Where(m => deep ||
                    Keywords.Any(k => m.Name.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0) ||
                    type.FullName.IndexOf("weapon", StringComparison.OrdinalIgnoreCase) >= 0)
                    .OrderBy(m => m.Name))
                {
                    string parameters = string.Join(", ", method.Parameters.Select(p => p.ParameterType.FullName + " " + p.Name));
                    output.WriteLine("  " + (method.IsPublic ? "public " : method.IsFamily ? "protected " : "nonpublic ")
                        + (method.IsStatic ? "static " : "") + method.ReturnType.FullName + " " +
                        method.Name + "(" + parameters + ")");
                }
                output.WriteLine();
            }
        }

        Console.WriteLine("Wrote whiplash-api-report.txt");
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