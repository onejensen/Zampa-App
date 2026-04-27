document.addEventListener('DOMContentLoaded', () => {
    // Stat Counters Animation
    const stats = document.querySelectorAll('.stat-number');
    const speed = 200;

    const animateStats = () => {
        stats.forEach(stat => {
            const updateCount = () => {
                const target = +stat.getAttribute('data-target');
                const count = +stat.innerText;
                const inc = target / speed;

                if (count < target) {
                    stat.innerText = Math.ceil(count + inc);
                    setTimeout(updateCount, 1);
                } else {
                    stat.innerText = target.toLocaleString();
                }
            };
            updateCount();
        });
    };

    // Intersection Observer for scroll animations
    const observerOptions = {
        threshold: 0.2
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                if (entry.target.classList.contains('stats')) {
                    animateStats();
                }
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    document.querySelectorAll('section').forEach(section => {
        observer.observe(section);
    });

    // Smooth Scroll for navigation
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                window.scrollTo({
                    top: target.offsetTop - 80,
                    behavior: 'smooth'
                });
            }
        });
    });

    // Navbar transparency check on scroll
    const header = document.querySelector('.header');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 50) {
            header.style.background = 'rgba(15, 15, 26, 0.95)';
            header.style.padding = '10px 0';
        } else {
            header.style.background = 'rgba(15, 15, 26, 0.8)';
            header.style.padding = '20px 0';
        }
    });
});
