using System;
using System.Linq;
using System.Reflection;
using UnityEngine;

namespace ArsenalExpansion
{
    /// <summary>
    /// First playable GODSPLITTER prototype. Creates its own simple sword mesh and effects,
    /// so no external asset bundle is required. Game damage APIs are resolved at runtime
    /// because ULTRAKILL's internal signatures vary between game versions.
    /// </summary>
    public sealed class GodsplitterWeapon : MonoBehaviour
    {
        private Transform swordRoot;
        private Camera playerCamera;
        private float nextPrimaryTime;
        private float nextSmiteTime;
        private bool slashing;
        private float slashStarted;
        private Vector3 swordBasePosition;
        private Quaternion swordBaseRotation;
        private LineRenderer beam;
        private float beamUntil;
        private static readonly Color Gold = new Color(1f, 0.78f, 0.16f, 1f);
        private static readonly Color WhiteGold = new Color(1f, 0.96f, 0.72f, 1f);

        private void Start()
        {
            playerCamera = Camera.main;
            if (playerCamera == null)
                playerCamera = FindObjectOfType<Camera>();
            if (playerCamera == null)
            {
                Plugin.Log.LogWarning("GODSPLITTER: waiting for player camera.");
                return;
            }

            BuildSword();
            BuildBeam();
            Plugin.Log.LogInfo("GODSPLITTER prototype initialized.");
        }

        private void Update()
        {
            if (playerCamera == null)
            {
                playerCamera = Camera.main;
                if (playerCamera == null) return;
            }

            if (swordRoot == null)
                BuildSword();

            bool equipped = gameObject.activeInHierarchy;
            if (swordRoot != null)
                swordRoot.gameObject.SetActive(equipped);

            if (!equipped || swordRoot == null)
            {
                if (beam != null) beam.enabled = Time.time < beamUntil;
                return;
            }

            if (Input.GetMouseButtonDown(0) && Time.time >= nextPrimaryTime)
                PrimarySlash();

            if (Input.GetMouseButtonDown(1) && Time.time >= nextSmiteTime)
                Smite();

            AnimateSlash();
            if (beam != null && Time.time >= beamUntil)
                beam.enabled = false;
        }

        private void BuildSword()
        {
            if (playerCamera == null) return;

            GameObject root = new GameObject("GODSPLITTER_Sword_Viewmodel");
            swordRoot = root.transform;
            swordRoot.SetParent(playerCamera.transform, false);
            swordBasePosition = new Vector3(0.42f, -0.30f, 0.72f);
            swordBaseRotation = Quaternion.Euler(8f, -16f, -8f);
            swordRoot.localPosition = swordBasePosition;
            swordRoot.localRotation = swordBaseRotation;

            MakePart("Silver Hilt", PrimitiveType.Cylinder, swordRoot,
                new Vector3(0f, -0.12f, 0f), new Vector3(0.045f, 0.19f, 0.045f),
                Quaternion.Euler(0f, 0f, 0f), new Color(0.58f, 0.62f, 0.68f));
            MakePart("Gold Guard", PrimitiveType.Cube, swordRoot,
                new Vector3(0f, 0.06f, 0f), new Vector3(0.28f, 0.045f, 0.07f),
                Quaternion.Euler(0f, 0f, -8f), Gold);
            MakePart("Divine Blade", PrimitiveType.Cube, swordRoot,
                new Vector3(0f, 0.43f, 0.015f), new Vector3(0.12f, 0.66f, 0.035f),
                Quaternion.Euler(0f, 0f, 0f), WhiteGold);
            MakePart("Blue Blade Spine", PrimitiveType.Cube, swordRoot,
                new Vector3(0f, 0.42f, -0.012f), new Vector3(0.035f, 0.62f, 0.012f),
                Quaternion.Euler(0f, 0f, 0f), new Color(0.18f, 0.58f, 1f));
            MakePart("Blade Tip", PrimitiveType.Cube, swordRoot,
                new Vector3(0f, 0.78f, 0.015f), new Vector3(0.075f, 0.13f, 0.035f),
                Quaternion.Euler(0f, 0f, 45f), WhiteGold);
        }

        private static GameObject MakePart(string partName, PrimitiveType primitive, Transform parent,
            Vector3 position, Vector3 scale, Quaternion rotation, Color color)
        {
            GameObject part = GameObject.CreatePrimitive(primitive);
            part.name = "GODSPLITTER_" + partName;
            part.transform.SetParent(parent, false);
            part.transform.localPosition = position;
            part.transform.localScale = scale;
            part.transform.localRotation = rotation;
            Collider collider = part.GetComponent<Collider>();
            if (collider != null) Destroy(collider);
            Renderer renderer = part.GetComponent<Renderer>();
            if (renderer != null)
            {
                Shader shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                if (shader != null) renderer.material = new Material(shader);
                renderer.material.color = color;
                renderer.material.SetColor("_EmissionColor", color * 0.18f);
            }
            return part;
        }

        private void BuildBeam()
        {
            GameObject beamObject = new GameObject("GODSPLITTER_SMITE_Beam");
            beam = beamObject.AddComponent<LineRenderer>();
            beam.positionCount = 2;
            beam.startWidth = 0.7f;
            beam.endWidth = 0.08f;
            beam.material = new Material(Shader.Find("Sprites/Default"));
            beam.startColor = WhiteGold;
            beam.endColor = Gold;
            beam.useWorldSpace = true;
            beam.enabled = false;
        }

        private void PrimarySlash()
        {
            nextPrimaryTime = Time.time + 0.34f;
            slashing = true;
            slashStarted = Time.time;
            Vector3 origin = playerCamera.transform.position;
            Vector3 direction = playerCamera.transform.forward;
            RaycastHit hit;
            if (Physics.SphereCast(origin, 0.42f, direction, out hit, 3.4f,
                Physics.DefaultRaycastLayers, QueryTriggerInteraction.Collide))
            {
                TryDamage(hit.collider.gameObject, 1.0f, hit.point, direction * 3f);
                TryKillAfterimage(hit.collider.gameObject, hit.point, direction);
            }
            else
            {
                Collider[] nearby = Physics.OverlapSphere(origin + direction * 2.0f, 0.85f);
                foreach (Collider candidate in nearby)
                {
                    if (candidate != null && candidate.transform.root != transform.root)
                    {
                        TryDamage(candidate.gameObject, 1.0f, candidate.ClosestPoint(origin), direction * 3f);
                        break;
                    }
                }
            }
        }

        private void Smite()
        {
            nextSmiteTime = Time.time + 2.8f;
            Vector3 origin = playerCamera.transform.position;
            Vector3 direction = playerCamera.transform.forward;
            RaycastHit hit;
            Vector3 point = origin + direction * 35f;
            GameObject target = null;
            if (Physics.Raycast(origin, direction, out hit, 60f, Physics.DefaultRaycastLayers,
                QueryTriggerInteraction.Collide))
            {
                point = hit.point;
                target = hit.collider.gameObject;
            }

            Vector3 beamStart = point + Vector3.up * 8f;
            if (beam != null)
            {
                beam.SetPosition(0, beamStart);
                beam.SetPosition(1, point);
                beam.enabled = true;
                beamUntil = Time.time + 0.22f;
            }

            GameObject flash = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            flash.name = "GODSPLITTER_SMITE_Impact";
            flash.transform.position = point;
            flash.transform.localScale = Vector3.one * 1.3f;
            Collider flashCollider = flash.GetComponent<Collider>();
            if (flashCollider != null) Destroy(flashCollider);
            Renderer flashRenderer = flash.GetComponent<Renderer>();
            if (flashRenderer != null)
            {
                flashRenderer.material = new Material(Shader.Find("Sprites/Default"));
                flashRenderer.material.color = WhiteGold;
            }
            Destroy(flash, 0.14f);

            if (target != null)
                TryDamage(target, 3.0f, point, Vector3.up * 5f);
        }

        private void AnimateSlash()
        {
            if (!slashing || swordRoot == null) return;
            float t = (Time.time - slashStarted) / 0.22f;
            if (t >= 1f)
            {
                slashing = false;
                swordRoot.localPosition = swordBasePosition;
                swordRoot.localRotation = swordBaseRotation;
                return;
            }

            float arc = Mathf.Sin(t * Mathf.PI);
            swordRoot.localPosition = swordBasePosition + new Vector3(-0.20f * arc, 0.04f * arc, 0f);
            swordRoot.localRotation = swordBaseRotation * Quaternion.Euler(0f, 0f, -105f + 210f * t);
        }

        private void TryKillAfterimage(GameObject hitObject, Vector3 hitPoint, Vector3 direction)
        {
            Component enemy = FindEnemyIdentifier(hitObject);
            if (enemy == null) return;
            float before = ReadHealth(enemy);
            if (before <= 0f) return;

            // The enemy may die immediately or finish its death routine on the next frame.
            StartCoroutine(AfterimageCheck(enemy, hitPoint, direction, before));
        }

        private System.Collections.IEnumerator AfterimageCheck(Component enemy, Vector3 hitPoint,
            Vector3 direction, float before)
        {
            yield return null;
            if (enemy == null || ReadHealth(enemy) > 0f) yield break;

            Component nearest = FindNearestEnemy(hitPoint, 7f, enemy.gameObject);
            if (nearest == null) yield break;

            GameObject afterimage = GameObject.CreatePrimitive(PrimitiveType.Cube);
            afterimage.name = "GODSPLITTER_Golden_Afterimage";
            afterimage.transform.position = hitPoint + direction * 0.45f;
            afterimage.transform.rotation = Quaternion.LookRotation(direction);
            afterimage.transform.localScale = new Vector3(0.12f, 0.8f, 0.08f);
            Collider c = afterimage.GetComponent<Collider>();
            if (c != null) Destroy(c);
            Renderer r = afterimage.GetComponent<Renderer>();
            if (r != null)
            {
                r.material = new Material(Shader.Find("Sprites/Default"));
                r.material.color = Gold;
            }
            Destroy(afterimage, 0.18f);
            TryDamage(nearest.gameObject, 0.7f, nearest.transform.position, direction * 2f);
        }

        private static Component FindNearestEnemy(Vector3 origin, float range, GameObject exclude)
        {
            Component best = null;
            float bestDistance = range;
            foreach (Component component in FindObjectsOfType<MonoBehaviour>())
            {
                if (component == null || component.gameObject == exclude ||
                    component.GetType().Name != "EnemyIdentifier") continue;
                float distance = Vector3.Distance(origin, component.transform.position);
                if (distance < bestDistance && ReadHealth(component) > 0f)
                {
                    best = component;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private static Component FindEnemyIdentifier(GameObject obj)
        {
            if (obj == null) return null;
            Component found = obj.GetComponent("EnemyIdentifier");
            if (found != null) return found;
            found = obj.GetComponentInParent(typeof(MonoBehaviour));
            if (found != null && found.GetType().Name == "EnemyIdentifier") return found;
            foreach (Component component in obj.GetComponentsInParent<MonoBehaviour>(true))
                if (component != null && component.GetType().Name == "EnemyIdentifier") return component;
            return null;
        }

        private static float ReadHealth(Component enemy)
        {
            if (enemy == null) return -1f;
            string[] names = { "health", "healthBar", "hp" };
            Type type = enemy.GetType();
            foreach (string name in names)
            {
                FieldInfo field = type.GetField(name, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
                if (field != null)
                {
                    object value = field.GetValue(enemy);
                    if (value is float) return (float)value;
                    if (value is int) return (int)value;
                    if (value != null)
                    {
                        FieldInfo nested = value.GetType().GetField("hp", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
                        if (nested != null && nested.GetValue(value) is float) return (float)nested.GetValue(value);
                    }
                }
                PropertyInfo property = type.GetProperty(name, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
                if (property != null && property.CanRead)
                {
                    object value = property.GetValue(enemy, null);
                    if (value is float) return (float)value;
                    if (value is int) return (int)value;
                }
            }
            return -1f;
        }

        private void TryDamage(GameObject target, float multiplier, Vector3 hitPoint, Vector3 force)
        {
            Component enemy = FindEnemyIdentifier(target);
            if (enemy == null) return;
            Type type = enemy.GetType();
            MethodInfo[] methods = type.GetMethods(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)
                .Where(m => m.Name == "DeliverDamage" || m.Name == "Damage" || m.Name == "GetHurt")
                .OrderBy(m => m.GetParameters().Length).ToArray();

            foreach (MethodInfo method in methods)
            {
                ParameterInfo[] parameters = method.GetParameters();
                object[] args = new object[parameters.Length];
                bool supported = true;
                for (int i = 0; i < parameters.Length; i++)
                {
                    Type p = parameters[i].ParameterType;
                    if (p == typeof(Vector3))
                    {
                        string n = parameters[i].Name == null ? "" : parameters[i].Name.ToLowerInvariant();
                        args[i] = n.Contains("force") || n.Contains("velocity") ? (object)force : hitPoint;
                    }
                    else if (p == typeof(float)) args[i] = multiplier;
                    else if (p == typeof(int)) args[i] = 0;
                    else if (p == typeof(bool)) args[i] = false;
                    else if (p == typeof(GameObject)) args[i] = gameObject;
                    else if (p == typeof(Transform)) args[i] = transform;
                    else if (p.IsEnum) args[i] = Enum.ToObject(p, 0);
                    else if (parameters[i].HasDefaultValue) args[i] = parameters[i].DefaultValue;
                    else if (!p.IsValueType) args[i] = null;
                    else { supported = false; break; }
                }
                if (!supported) continue;
                try { method.Invoke(enemy, args); return; }
                catch (Exception ex) { Plugin.Log.LogDebug("GODSPLITTER damage signature rejected: " + ex.GetType().Name); }
            }
            Plugin.Log.LogWarning("GODSPLITTER found an enemy but could not match its damage API on this game version.");
        }

        private void OnDestroy()
        {
            if (swordRoot != null) Destroy(swordRoot.gameObject);
            if (beam != null) Destroy(beam.gameObject);
        }
    }
}
