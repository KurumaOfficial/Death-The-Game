# Render Death

> Stage 1.

## Цель

Vulkan 1.2+ как основной API на всех платформах:
- desktop — нативный Vulkan через LWJGL `org.lwjgl.vulkan.*`;
- Android — нативный Vulkan через NDK (на стадии 2);
- iOS — Vulkan поверх Metal через MoltenVK (на стадии 2).

OpenGL/GLES backend не планируется; если потребуется fallback —
рассмотрим WebGPU через `wgpu-native` на стадии 3+.

## Уровни абстракции

```
WeTTeA.api.render.RenderContext        (контракт)
WeTTeA.api.render.RenderTarget         (swapchain image / RT)
WeTTeA.api.render.MeshHandle           (opaque vbo handle)
WeTTeA.api.render.MaterialHandle       (pipeline + descriptor sets)
WeTTeA.api.render.RenderFrame          (command recording context)
        ▲
        │ implements
        │
WeTTeA.platform.desktop.render.VulkanRenderContext  (stage 2)
WeTTeA.platform.android.render.AndroidVulkanContext (stage 2)
WeTTeA.platform.ios.render.MoltenVkContext          (stage 2)
```

## Stage 1 — что есть

- LWJGL `org.lwjgl.vulkan.*` подключён в `:desktop` бандлом libs.bundles.lwjgl.jvm.
- `VulkanInstanceBootstrap` создаёт `VkInstance` и перечисляет физические
  устройства — это smoke-проверка что loader доступен.

## Stage 1 — чего нет

- swapchain (`VkSwapchainKHR`);
- logical device (`VkDevice`) с graphics queue;
- render-pass / framebuffers;
- shader compilation (glslc → SPIR-V);
- pipeline cache;
- frame pacing (semaphore + fence);
- любая отрисовка геометрии.

Все эти пункты — INTEGRATION_MISSING, см. `PROGRESS.md`.

## Stage 2 порядок задач

1. `VkPhysicalDevice` selection (graphics + present queue, swapchain extension).
2. `VkDevice` + `VkQueue` (graphics + present, может совпадать).
3. `VkSurfaceKHR` через `glfwCreateWindowSurface`.
4. `VkSwapchainKHR` (image format SRGB, present mode FIFO).
5. Render-pass с одним color attachment (clear → present).
6. Command pool + command buffers per frame in flight.
7. Triangle hello-world (vertex + fragment shader, hardcoded geometry).
8. Резулльтат: окно с цветным треугольником, 60 FPS, корректный shutdown.
